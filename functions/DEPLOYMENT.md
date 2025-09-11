# 🚀 Deployment Guide - Firebase Functions Gen 2

## 🔧 Quick Setup

### 1. Install Dependencies
```bash
cd functions
npm install
```

### 2. Build
```bash
npm run build
```

### 3. Deploy
```bash
# Deploy to development
firebase use dev
firebase deploy --only functions

# Deploy to production
firebase use prod
firebase deploy --only functions
```

## 📊 Monitoring

### View Logs
```bash
# All functions
firebase functions:log

# Specific functions
firebase functions:log --only onProductCostUpdate
firebase functions:log --only onRecipeCostUpdate
```

### Check Deployment
```bash
# List deployed functions
firebase functions:list

# Check current project
firebase use
```

## 🚨 Troubleshooting

### Build Errors
```bash
# Clean and rebuild
cd functions
rm -rf node_modules lib
npm install
npm run build
```

### Deployment Errors
```bash
# Check authentication
firebase login

# Check project
firebase use

# Redeploy
firebase deploy --only functions
```

## 📝 Functions Overview

### `onProductCostUpdate`
- **Trigger:** Product cost changes
- **Action:** Updates direct recipes containing the product
- **Region:** `southamerica-east1`

### `onRecipeCostUpdate`
- **Trigger:** Recipe cost changes
- **Action:** Updates parent recipes containing the updated recipe
- **Region:** `southamerica-east1`


## ✅ Deployment Checklist

- [ ] Dependencies installed (`npm install`)
- [ ] Code compiled (`npm run build`)
- [ ] Correct environment selected (`firebase use`)
- [ ] Functions deployed (`firebase deploy --only functions`)
- [ ] Logs working (`firebase functions:log`)
- [ ] Test updates working correctly