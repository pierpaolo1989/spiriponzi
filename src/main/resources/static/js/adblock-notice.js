(function () {
    const noticeKey = "spiriponzi-adblock-notice-dismissed";
    const modal = $("#adblockNoticeModal");

    function wasNoticeDismissed() {
        try {
            return window.sessionStorage.getItem(noticeKey) === "true";
        } catch (error) {
            return false;
        }
    }

    function rememberDismissal() {
        try {
            window.sessionStorage.setItem(noticeKey, "true");
        } catch (error) {
            // The notice remains dismissible even when browser storage is unavailable.
        }
    }

    function hasAdBlocker() {
        const bait = document.createElement("div");
        bait.className = "adsbox ad-banner ad-unit advertisement";
        bait.setAttribute("aria-hidden", "true");
        bait.style.cssText = "position:absolute;top:-10000px;left:-10000px;width:10px;height:10px;";
        document.body.appendChild(bait);

        const style = window.getComputedStyle(bait);
        const blocked = bait.offsetHeight === 0
            || bait.offsetWidth === 0
            || style.display === "none"
            || style.visibility === "hidden";
        bait.remove();

        return blocked;
    }

    function showNoticeIfNeeded(adBlockDetected) {
        if (adBlockDetected && !wasNoticeDismissed()) {
            modal.modal("show");
        }
    }

    $(function () {
        modal.on("hidden.bs.modal", rememberDismissal);

        const adBlockDetected = hasAdBlocker();
        if (navigator.brave && typeof navigator.brave.isBrave === "function") {
            Promise.resolve()
                .then(function () {
                    return navigator.brave.isBrave();
                })
                .then(function (isBrave) {
                    showNoticeIfNeeded(adBlockDetected || isBrave);
                })
                .catch(function () {
                    showNoticeIfNeeded(adBlockDetected);
                });
            return;
        }

        showNoticeIfNeeded(adBlockDetected);
    });
})();
