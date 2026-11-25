(function () {
    const modal = $("#adblockNoticeModal");
    let blockingNotice = false;

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
        if (adBlockDetected) {
            blockingNotice = true;
            modal.modal("show");
        }
    }

    function retryAfterDisablingBlocker() {
        const status = $("#adblockNoticeStatus");
        if (hasAdBlocker()) {
            status.text("An ad blocker is still active. Disable it for this site, then try again.");
            return;
        }

        blockingNotice = false;
        modal.modal("hide");
    }

    $(function () {
        modal.modal({
            backdrop: "static",
            keyboard: false,
            show: false
        });
        modal.on("hide.bs.modal", function (event) {
            if (blockingNotice) {
                event.preventDefault();
            }
        });
        $("#adblockNoticeRetry").on("click", retryAfterDisablingBlocker);

        showNoticeIfNeeded(hasAdBlocker());
    });
})();
