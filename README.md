# Flow Budget

Privacy-first Android budgeting app.

## MVP
- Dashboard: income, expenses, balance
- Manual transactions
- Bank SMS monitoring using a user-controlled sender allow-list
- Debit/credit + amount parsing
- Monthly budget
- Basic/Premium demo feature gate
- Local-only transaction storage
- GitHub Actions debug APK build

Bank SMS is processed on-device. No banking password is required.

## Testing
Install the debug APK, grant SMS permission, open Banks, and add the exact sender IDs/numbers you approve.

## Play Store
Google Play restricts SMS permissions. Production distribution must meet Google's SMS permission policy or use an alternative import mechanism.
