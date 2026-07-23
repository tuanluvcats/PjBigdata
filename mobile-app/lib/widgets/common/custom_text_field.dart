import 'package:flutter/material.dart';

import 'package:finance_ai_app/core/theme/app_theme.dart';

class CustomTextField extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final bool isPassword;
  final String obscuringCharacter;
  final ValueChanged<String>? onChanged;
  final TextInputType? keyboardType;
  final Widget? suffixIcon;
  final bool obscureText;

  const CustomTextField({
    super.key,
    required this.controller,
    required this.label,
    this.isPassword = false,
    this.obscuringCharacter = '•',
    this.onChanged,
    this.keyboardType,
    this.suffixIcon,
    this.obscureText = false,
  }) : assert(obscuringCharacter.length == 1);

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      keyboardType: keyboardType,
      obscureText: isPassword || obscureText,
      obscuringCharacter: obscuringCharacter,
      enableSuggestions: !isPassword,
      autocorrect: !isPassword,
      onChanged: onChanged,
      style: const TextStyle(color: AppTheme.textWhite),
      decoration: InputDecoration(
        labelText: label,
        labelStyle: const TextStyle(color: AppTheme.textGray),
        filled: true,
        fillColor: AppTheme.navyLight,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(
            color: AppTheme.textGray.withValues(alpha: 0.3),
          ),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(
            color: AppTheme.textGray.withValues(alpha: 0.3),
          ),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppTheme.mintAccent),
        ),
        suffixIcon: suffixIcon,
      ),
    );
  }
}
