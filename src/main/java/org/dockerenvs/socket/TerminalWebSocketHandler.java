package org.dockerenvs.socket;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

/**
 * WebSocket 终端处理器：将前端xterm.js与Docker容器双向桥接
 */
@Slf4j
@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final DockerClient dockerClient;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, TerminalSessionContext> sessionContexts = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String containerId = resolveContainerId(session);
        if (!StringUtils.hasText(containerId)) {
            log.warn("WebSocket缺少containerId参数");
            session.close(new CloseStatus(4000, "containerId 参数必填"));
            return;
        }

        PipedInputStream containerInput = new PipedInputStream(16 * 1024);
        PipedOutputStream clientWriter = new PipedOutputStream(containerInput);

        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame frame) {
                if (frame == null || frame.getPayload() == null) {
                    return;
                }
                try {
                    if (session.isOpen()) {
                        String payload = new String(frame.getPayload(), StandardCharsets.UTF_8);
                        session.sendMessage(new TextMessage(payload));
                    }
                } catch (IOException e) {
                    log.warn("发送容器输出到WebSocket失败", e);
                    safeCloseSession(session, CloseStatus.SERVER_ERROR);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Docker Exec 发生错误", throwable);
                safeCloseSession(session, CloseStatus.SERVER_ERROR);
            }

            @Override
            public void onComplete() {
                log.info("Docker Exec 已结束: containerId={}", containerId);
                safeCloseSession(session, CloseStatus.NORMAL);
            }
        };

        Future<?> streamingTask = executorService.submit(() -> {
            try {
                attachShellWithFallback(containerId, containerInput, callback);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("终端任务被中断: {}", containerId);
            } catch (NotFoundException e) {
                log.warn("终端连接失败，容器不存在或已停止: {}", containerId);
                safeCloseSession(session, new CloseStatus(4004, "容器不存在或已停止"));
            } catch (Exception e) {
                log.error("执行容器终端失败: {}", containerId, e);
                safeCloseSession(session, CloseStatus.SERVER_ERROR);
            }
        });

        sessionContexts.put(session.getId(), new TerminalSessionContext(clientWriter, callback, streamingTask));

        log.info("WebSocket 终端连接建立: sessionId={}, containerId={}", session.getId(), containerId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        TerminalSessionContext context = sessionContexts.get(session.getId());
        if (context == null) {
            log.warn("未找到会话上下文: sessionId={}", session.getId());
            session.close(new CloseStatus(4001, "状态已失效"));
            return;
        }
        try {
            context.getClientWriter().write(message.getPayload().getBytes(StandardCharsets.UTF_8));
            context.getClientWriter().flush();
        } catch (IOException e) {
            log.error("写入容器stdin失败", e);
            safeCloseSession(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        cleanupSession(session.getId());
        log.info("WebSocket 终端连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket 传输异常: sessionId={}", session.getId(), exception);
        safeCloseSession(session, CloseStatus.SERVER_ERROR);
    }

    private String resolveContainerId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        return params.getFirst("containerId");
    }

    private void attachShellWithFallback(String containerId,
                                         PipedInputStream containerInput,
                                         ResultCallback.Adapter<Frame> callback) throws InterruptedException {
        String[][] candidates = new String[][] {
            {"/bin/bash"},
            {"/bin/sh"},
            {"sh"}
        };

        RuntimeException lastError = null;
        for (String[] cmd : candidates) {
            try {
                ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withTty(true)
                    .withCmd(cmd)
                    .exec();

                dockerClient.execStartCmd(exec.getId())
                    .withDetach(false)
                    .withTty(true)
                    .withStdIn(containerInput)
                    .exec(callback)
                    .awaitCompletion();
                return;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("启动命令 {} 失败，尝试下一个: {}", String.join(" ", cmd), e.getMessage());
            }
        }

        if (lastError != null) {
            throw lastError;
        }
    }

    private void safeCloseSession(WebSocketSession session, CloseStatus status) {
        if (session != null && session.isOpen()) {
            try {
                session.close(status);
            } catch (IOException e) {
                log.debug("关闭WebSocket失败", e);
            }
        }
    }

    private void cleanupSession(String sessionId) {
        TerminalSessionContext context = sessionContexts.remove(sessionId);
        if (context == null) {
            return;
        }
        try {
            context.getClientWriter().close();
        } catch (IOException e) {
            log.debug("关闭stdin写入器失败", e);
        }
        if (context.getCallback() != null) {
            try {
                context.getCallback().close();
            } catch (IOException e) {
                log.debug("关闭回调失败", e);
            }
        }
        if (context.getStreamingTask() != null) {
            context.getStreamingTask().cancel(true);
        }
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdownNow();
    }

    private static class TerminalSessionContext {
        private final PipedOutputStream clientWriter;
        private final ResultCallback.Adapter<Frame> callback;
        private final Future<?> streamingTask;

        TerminalSessionContext(PipedOutputStream clientWriter,
                               ResultCallback.Adapter<Frame> callback,
                               Future<?> streamingTask) {
            this.clientWriter = clientWriter;
            this.callback = callback;
            this.streamingTask = streamingTask;
        }

        public PipedOutputStream getClientWriter() {
            return clientWriter;
        }

        public ResultCallback.Adapter<Frame> getCallback() {
            return callback;
        }

        public Future<?> getStreamingTask() {
            return streamingTask;
        }
    }
}

