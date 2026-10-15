# Go-live checklist (the parts only you can do, ~20 min)
1. Create a Gumroad (or Stripe) account and a $19 product. Zip `storefront/product` and upload it there only, never to the public site. Gumroad delivers the file automatically.
2. Replace `CHECKOUT_URL_PLACEHOLDER` in `storefront/site/index.html` with your product link.
3. Repo Settings -> Pages -> Source: GitHub Actions. Merge to main; the workflow publishes the site.

Honest expectation: zero traffic means zero sales. Plan a few posts (Reddit r/sysadmin, LinkedIn) to start.
