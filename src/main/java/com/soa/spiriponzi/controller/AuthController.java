package com.soa.spiriponzi.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.soa.spiriponzi.dto.UpdateUserDto;
import com.soa.spiriponzi.dto.UserDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.UserService;

import jakarta.validation.Valid;

@Controller
public class AuthController {

	private UserService userService;

	public AuthController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("index")
	public String home(){
		return "index";
	}

	@GetMapping("/login")
	public String loginForm() {
		return "login";
	}

	@GetMapping("register")
	public String showRegistrationForm(Model model) {
		UserDto user = new UserDto();
		model.addAttribute("user", user);
		return "register";
	}

	@PostMapping("/register/save")
	public String registration(@Valid @ModelAttribute("user") UserDto user,
			BindingResult result,
			Model model){
		User existing = userService.findByEmail(user.getEmail());
		if (existing != null) {
			result.rejectValue("email", null, "There is already an account registered with that email");
		}
		if (result.hasErrors()) {
			model.addAttribute("user", user);
			return "register";
		}
		userService.saveUser(user);
		return "redirect:/register?success";
	}

	@PostMapping("/user-data")
	public String updateUserStats(@Valid @ModelAttribute("updateUser") UpdateUserDto user,
			BindingResult result,
			Model model){
		User existing = userService.findByEmail(user.getEmail());
		if (existing == null) {
			result.rejectValue("email", null, "No user was found registered with this email");
		}
		if (result.hasErrors()) {
			model.addAttribute("user", user);
			return "profile";
		}
		userService.updateUser(user);
		return "redirect:/profile?success";
	}

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final User user = userService.findByEmail(authentication.getName());
		model.addAttribute("user", user);
		return "dashboard";
	}
	
	@GetMapping("/surf-ads")
	public String surfAds(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final User user = userService.findByEmail(authentication.getName());
		model.addAttribute("user", user);
		return "surf-ads";
	}
	

	@GetMapping("/survey")
	public String survey(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final User user = userService.findByEmail(authentication.getName());
		model.addAttribute("user", user);
		return "survey";
	}

	@GetMapping("/withdraw-requests")
	public String withdrawRequests(Model model) {
		return "withdraw-requests";
	}

	@GetMapping("/recover-password")
	public String recoverPassword(Model model) {
		return "recover-password";
	}

	@GetMapping("/report")
	public String report(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final User user = userService.findByEmail(authentication.getName());
		model.addAttribute("user", user);
		return "report";
	}

	@GetMapping("/profile")
	public String profile(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final User user = userService.findByEmail(authentication.getName());
		model.addAttribute("user", user);
		final UpdateUserDto updateUser = UpdateUserDto.builder()
				.email(user.getEmail())
				.btcWallet(user.getBtcWallet())
				.ethWallet(user.getEthWallet())
				.build();
		model.addAttribute("updateUser", updateUser);
		return "profile";
	}
}