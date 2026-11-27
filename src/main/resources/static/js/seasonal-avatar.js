(() => {
	const avatarTargets = document.querySelectorAll('[data-seasonal-avatar]');
	if (!avatarTargets.length) {
		return;
	}

	const today = new Date();
	const year = today.getFullYear();
	const month = today.getMonth();
	const day = today.getDate();

	function easterSunday(year) {
		const a = year % 19;
		const b = Math.floor(year / 100);
		const c = year % 100;
		const d = Math.floor(b / 4);
		const e = b % 4;
		const f = Math.floor((b + 8) / 25);
		const g = Math.floor((b - f + 1) / 3);
		const h = (19 * a + b - d - g + 15) % 30;
		const i = Math.floor(c / 4);
		const k = c % 4;
		const l = (32 + 2 * e + 2 * i - h - k) % 7;
		const m = Math.floor((a + 11 * h + 22 * l) / 451);
		const value = h + l - 7 * m + 114;
		return new Date(year, Math.floor(value / 31) - 1, (value % 31) + 1);
	}

	function dayNumber(date) {
		return Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86400000);
	}

	function isEasterSeason() {
		const easter = easterSunday(year);
		const start = new Date(year, easter.getMonth(), easter.getDate() - 7);
		const end = new Date(year, easter.getMonth(), easter.getDate() + 1);
		const current = new Date(year, month, day);
		return dayNumber(current) >= dayNumber(start) && dayNumber(current) <= dayNumber(end);
	}

	let season = null;
	if ((month === 11 && day >= 1) || (month === 0 && day <= 6)) {
		season = { label: 'Christmas hat', icon: 'christmas-hat.svg' };
	} else if ((month === 9 && day >= 20) || (month === 10 && day <= 2)) {
		season = { label: 'Halloween pumpkin', icon: 'halloween-pumpkin.svg' };
	} else if (isEasterSeason()) {
		season = { label: 'Easter egg', icon: 'easter-egg.svg' };
	} else if ((month === 5 && day >= 21) || month === 6 || month === 7
			|| (month === 8 && day <= 22)) {
		season = { label: 'Summer sunglasses', icon: 'summer-sunglasses.svg' };
	}

	if (!season) {
		return;
	}

	avatarTargets.forEach((avatar) => {
		const badge = document.createElement('img');
		badge.className = 'seasonal-avatar-badge';
		badge.src = `/img/seasonal/${season.icon}`;
		badge.alt = '';
		badge.title = season.label;
		badge.setAttribute('role', 'img');
		badge.setAttribute('aria-label', season.label);
		avatar.appendChild(badge);
	});
})();
