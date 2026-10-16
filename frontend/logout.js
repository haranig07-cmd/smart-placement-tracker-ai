(function() {
    const authenticationKeys = [
        "userId",
        "userRole",
        "companyName",
        "userName",
        "userEmail"
    ];

    const API_BASE =
        "http://" + window.location.hostname + ":8080";

    document.addEventListener("click", async function(event) {
        const target = event.target instanceof Element ?
            event.target.closest("[data-logout]") :
            null;

        if (!target) {
            return;
        }

        event.preventDefault();

        if (target.dataset.loggingOut === "true") {
            return;
        }

        target.dataset.loggingOut = "true";

        try {
            const response = await fetch(
                API_BASE + "/api/users/logout", {
                    method: "POST",
                    credentials: "include"
                }
            );

            if (!response.ok) {
                throw new Error("Logout request failed");
            }

            authenticationKeys.forEach(function(key) {
                localStorage.removeItem(key);
            });

            window.location.assign(
                target.getAttribute("href") || "index.html"
            );

        } catch (error) {
            target.dataset.loggingOut = "false";
            alert("Unable to log out. Please try again.");
        }
    });
})();