package com.windwatch.scada.controller;

import com.windwatch.scada.model.User;
import com.windwatch.scada.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public String accountPage(Authentication auth, Model model) {
        userRepository.findByUsername(auth.getName())
                .ifPresent(u -> model.addAttribute("user", u));
        model.addAttribute("pageTitle", "내 계정");
        model.addAttribute("activePage", "account");
        return "account";
    }

    @PostMapping("/change-password")
    public String changePassword(Authentication auth,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMsg", "새 비밀번호가 일치하지 않습니다.");
            return "redirect:/account";
        }
        if (newPassword.length() < 4) {
            ra.addFlashAttribute("errorMsg", "비밀번호는 4자 이상이어야 합니다.");
            return "redirect:/account";
        }
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            ra.addFlashAttribute("errorMsg", "현재 비밀번호가 올바르지 않습니다.");
            return "redirect:/account";
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        ra.addFlashAttribute("successMsg", "비밀번호가 변경되었습니다.");
        return "redirect:/account";
    }

    @PostMapping("/change-email")
    public String changeEmail(Authentication auth,
                              @RequestParam String email,
                              RedirectAttributes ra) {
        userRepository.findByUsername(auth.getName()).ifPresent(u -> {
            u.setEmail(email);
            userRepository.save(u);
        });
        ra.addFlashAttribute("successMsg", "이메일이 변경되었습니다.");
        return "redirect:/account";
    }
}
