package com.soa.spiriponzi.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.soa.spiriponzi.dto.WithdrawDto;
import com.soa.spiriponzi.service.WithdrawService;
import com.soa.spiriponzi.service.CaptchaService;
import com.soa.spiriponzi.exception.InvalidReCaptchaException;
import com.soa.spiriponzi.exception.ReCaptchaInvalidException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.soa.spiriponzi.model.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/withdraws")
public class WihdrawController {
	
	private final WithdrawService withdrawService;
	private final CaptchaService captchaService;
	@Value("${withdrawals.allow-test-captcha:false}")
	private boolean allowTestCaptcha;
	
	@GetMapping
	public List<WithdrawDto> findMenuByUser(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
											@RequestParam(value = "size", required = false, defaultValue = "20") int size) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return withdrawService.findByUser(authentication.getName(), page, size);
	}
	
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> startWithdrawRequest(@RequestBody WithdrawDto request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String siteKey = captchaService.getReCaptchaSite();
		String secretKey = captchaService.getReCaptchaSecret();
		if (!StringUtils.hasText(siteKey) || !StringUtils.hasText(secretKey)
				|| (!allowTestCaptcha && ("6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI".equals(siteKey)
					|| "6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe".equals(secretKey)))) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Withdrawal CAPTCHA is not configured");
		}
		try {
			captchaService.processResponse(request.getCaptchaResponse());
		} catch (InvalidReCaptchaException | ReCaptchaInvalidException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CAPTCHA verification failed", ex);
		}
		withdrawService.startRequest(authentication.getName(), request);
		return ResponseEntity.ok(null);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ErrorResponse> handleWithdrawalError(ResponseStatusException ex) {
		return ResponseEntity.status(ex.getStatusCode()).body(
				new ErrorResponse("WITHDRAWAL_ERROR", ex.getReason(), ex.getStatusCode().value()));
	}
	
}
