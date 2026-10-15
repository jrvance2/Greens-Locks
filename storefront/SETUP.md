# Go-live checklist (the parts only you can do, ~20 min)
1. Stripe (or Gumroad) account -> create a $19 product with the zip uploaded for automatic delivery
   (Gumroad delivers files automatically; Stripe Payment Links need a post-purchase redirect page).
2. Replace `CHECKOUT_URL_PLACEHOLDER` in `storefront/site/index.html` with your payment link.
3. Repo Settings -> Pages -> Source: GitHub Actions. Merge to main; the workflow publishes the site.
4. Note: the delivery zip is also built into the public site, so for a paid product host the zip
   ONLY on Gumroad/Stripe and remove the zip step from the workflow before launch.
Honest expectation: zero traffic = zero sales. Plan a few posts (Reddit r/sysadmin, LinkedIn) to start.
