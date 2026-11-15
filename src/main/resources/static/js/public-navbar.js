document.querySelectorAll(".public-navbar").forEach((navbar) => {
	const toggle = navbar.querySelector(".public-nav-toggle");
	const links = navbar.querySelector(".public-nav-links");

	if (!toggle || !links) {
		return;
	}

	const setOpen = (open) => {
		links.classList.toggle("is-open", open);
		toggle.setAttribute("aria-expanded", String(open));
		toggle.setAttribute(
			"aria-label",
			open ? "Close navigation menu" : "Open navigation menu"
		);
		toggle.innerHTML = `<i class="fas fa-${open ? "times" : "bars"}" aria-hidden="true"></i>`;
	};

	toggle.addEventListener("click", () => {
		setOpen(toggle.getAttribute("aria-expanded") !== "true");
	});

	links.addEventListener("click", (event) => {
		if (event.target.closest("a")) {
			setOpen(false);
		}
	});

	navbar.addEventListener("keydown", (event) => {
		if (event.key === "Escape" && toggle.getAttribute("aria-expanded") === "true") {
			setOpen(false);
			toggle.focus();
		}
	});

	document.addEventListener("click", (event) => {
		if (!navbar.contains(event.target)) {
			setOpen(false);
		}
	});
});
