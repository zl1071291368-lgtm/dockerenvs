package org.dockerenvs.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面控制器
 */
@Controller
public class PageController {
    
    /**
     * 首页 - 重定向到管理页面
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }
    
    /**
     * 管理页面
     */
    @GetMapping("/admin")
    public String admin() {
        return "redirect:/index.html";
    }
}

