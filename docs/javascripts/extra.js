document$.subscribe(() => {
    const portfolioLink = document.querySelector(
        'a[href="https://walker-systems.github.io/"], a[href="https://walker-systems.github.io"]'
    );

    if (!portfolioLink) return;

    portfolioLink.setAttribute("title", "Back to Portfolio");
    portfolioLink.setAttribute("aria-label", "Back to Portfolio");
    portfolioLink.setAttribute("target", "_blank");
    portfolioLink.setAttribute("rel", "noopener noreferrer");
});
