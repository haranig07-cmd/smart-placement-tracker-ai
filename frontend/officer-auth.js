window.officerFetch = async function(url, options = {}) {

    const response = await fetch(url, {
        ...options,
        credentials: "include"
    });

    if (response.status === 403) {

        let message =
            document.getElementById(
                "officerAccessMessage"
            );

        if (!message) {

            message =
                document.createElement("div");

            message.id =
                "officerAccessMessage";

            message.setAttribute(
                "role",
                "alert"
            );

            message.style.cssText =
                "position:fixed;" +
                "top:16px;" +
                "left:50%;" +
                "transform:translateX(-50%);" +
                "z-index:10000;" +
                "max-width:calc(100vw - 32px);" +
                "box-sizing:border-box;" +
                "padding:12px 16px;" +
                "background:#fff4e5;" +
                "border:1px solid #f0ad4e;" +
                "color:#513b13;" +
                "border-radius:4px;" +
                "text-align:center;";

            document.body.appendChild(
                message
            );
        }

        message.textContent =
            "Officer access required. " +
            "Please log in as a placement officer.";
    }

    return response;
};