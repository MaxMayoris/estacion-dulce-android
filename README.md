# 🍰 Estación Dulce - Bakery Management

An Android application for comprehensive bakery management.

## 📱 Description

Estación Dulce is a mobile application designed to manage all aspects of a bakery, from product inventory to recipes, customers, and business transactions.

## ✨ Main Features

- **Product Management**: Catalog and inventory
- **Recipe Management**: Creation and cost calculation
- **Customer Management**: Registration and contact
- **Transactions**: Input and output control
- **Kitchen Orders**: Order tracking and status management
- **Shipment Management**: Delivery tracking and status updates
- **Dashboard**: Business overview

## 🛠️ Technologies

- Kotlin
- Android
- Firebase
- Material Design

## 📋 Requirements

- Android 11.0 or higher
- Internet connection

## 🚀 Installation

1. Clone the repository
2. Configure Firebase
3. Build with Android Studio

## 🔐 Security

- Secure authentication
- Data validation
- Protected sensitive files
- API keys and configuration securely managed
- `.gitignore` properly configured to prevent credential leaks

## 🤝 Contributing

1. Fork the project
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License. See the `LICENSE` file for details.

## 👨‍💻 Author

**Maximiliano Roldan**
- Email: maxir.unsj@gmail.com
- GitHub: [@MaxMayoris](https://github.com/MaxMayoris)

## 📞 Support

If you have questions or need help, you can:
- Open an issue on GitHub
- Contact the developer by email

## 🔄 Version History

- **v4.1** - Added customizable recipes feature with optional product/cost validation bypass, improved address management with draft mode for person creation, enhanced Google Maps integration with dropdown address suggestions, updated movement item pricing to use 1.0 as default instead of 0.01, and implemented consistent modal styling across status change dialogs
- **v4.0** - Added product sales with salePrice field, implemented unified product/recipe search in sales, created shipment management system with status tracking, enhanced ProductEditActivity with improved UI layout, and implemented comprehensive kitchen orders system with automatic order creation, status tracking, and delivery integration
- **v3.5** - Enhanced RecipeEditActivity with modal product selection, improved table height consistency across all fragments using ConstraintLayout, updated delete button styling, and optimized column labels for better mobile display
- **v3.4** - Fixed shipment data loading when editing existing movements
- **v3.3** - Enhanced PersonEditActivity with tabbed interface for information and movements, improved PersonFragment with phone columns for both clients and providers, added movement deletion validation to prevent orphaned records, implemented auto-configuration of movement types based on person type, and standardized deletion dialog text formatting
- **v3.2** - Enhanced movement editing with date/time picker, improved quantity input validation (allows values > 0 with up to 3 decimals), and added dedicated tax section for purchases with automatic calculation and 21% default
- **v3.1** - Fixed infinite loader issue in HomeActivity, standardized all save buttons to "GUARDAR" with consistent styling, and enlarged loader icon size
- **v3.0** - Firebase Cloud Functions integration for automatic cost cascading and stock management, modern UI redesign for movement/recipe editing, person type standardization, movement date handling improvements, full-screen custom loader, comprehensive code cleanup and comment optimization
- **v2.2** - Multiple image support for recipes, profit percentage calculation, modernized delete dialogs, enhanced table formatting, and code cleanup
- **v2.1** - Minor corrections and bug fixes
- **v2.0** - Complete UI/UX design refactoring

---

⭐ If you like this project, give it a star on GitHub!
