# Go-live checklist (the parts only you can do, ~20 min)
0. Build the PDFs: `cd storefront/src && npm i playwright && node build.js` (output lands in `storefront/product/`, which is git-ignored so the paid files never reach the public repo).
1. Create a Gumroad (or Stripe) account and a $9 to $19 product. Upload the four PDFs (or one zip) there only. Gumroad delivers automatically.
2. Replace `CHECKOUT_URL_PLACEHOLDER` in `storefront/site/index.html` with your product link (and adjust the price on the button).
3. Repo Settings -> Pages -> Source: GitHub Actions. Merge to main; the workflow publishes the site.

Honest expectation: zero traffic means zero sales. Plan a few posts (Pinterest pins, Reddit personal-finance threads) to start.
