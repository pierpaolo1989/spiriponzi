package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.ADDto;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.soa.spiriponzi.dto.RecoverPasswordDto;
import com.soa.spiriponzi.dto.UpdateUserDto;
import com.soa.spiriponzi.dto.UserDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.UserService;

import jakarta.validation.Valid;

import java.util.List;

@Controller
public class AuthController {

	private final UserService userService;

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
			result.rejectValue("email", null, "There is already an account registered with this email");
		}
		if (result.hasErrors()) {
			model.addAttribute("user", user);
			return "register";
		}
		userService.saveUser(user);
		return "redirect:/register?success";
	}
	
	@PostMapping("/recover-password/save")
	public String registration(@Valid @ModelAttribute("passowrd") RecoverPasswordDto password,
			BindingResult result,
			Model model){
		User existing = userService.findByEmail(password.getEmail());
		if (existing == null) {
			result.rejectValue("email", null, "There is no account registered with this email");
		}
		if (result.hasErrors()) {
			model.addAttribute("password", password);
			return "recover-password";
		}
		userService.recoverPassword(password);
		return "redirect:/recover-password?success";
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
		List<ADDto> ads = userService.findAdsByUser(user.getId());
		model.addAttribute("ads", ads);
		return "surf-ads";
	}

	@GetMapping("/survey")
	public String survey(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final User user = userService.findByEmail(authentication.getName());
		model.addAttribute("user", user);
		return "survey";
	}

	@GetMapping("/academy")
	public String academy(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final User user = userService.findByEmail(authentication.getName());
		model.addAttribute("user", user);
		return "academy";
	}

	@GetMapping("/withdraw-requests")
	public String withdrawRequests(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final User user = userService.findByEmail(authentication.getName());
		model.addAttribute("user", user);
		return "withdraw-requests";
	}

	@GetMapping("/recover-password")
	public String recoverPassword(Model model) {
		RecoverPasswordDto password = new RecoverPasswordDto();
		model.addAttribute("password", password);
		return "recover-password";
	}

	@GetMapping("/report")
	public String report(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final User user = userService.findByEmail(authentication.getName());
		model.addAttribute("user", user);
		return "report";
	}
	
	@GetMapping("/games")
	public String games(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		final User user = userService.findByEmail(authentication.getName());
		model.addAttribute("user", user);
		return "games";
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