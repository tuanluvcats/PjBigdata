import 'package:flutter/material.dart';
import 'package:finance_ai_app/config/app_config.dart';
import 'package:finance_ai_app/core/theme/app_theme.dart';
import 'package:finance_ai_app/models/user_model.dart';
import 'package:finance_ai_app/services/sqflite_helper.dart';
import 'package:finance_ai_app/services/app_repository.dart';
import 'package:finance_ai_app/widgets/common/custom_text_field.dart';
import 'package:finance_ai_app/widgets/common/primary_button.dart';

enum _EditingField { email, password }

class AccountCredentialsScreen extends StatefulWidget {
  final int userId;

  const AccountCredentialsScreen({super.key, required this.userId});

  @override
  State<AccountCredentialsScreen> createState() =>
      _AccountCredentialsScreenState();
}

class _AccountCredentialsScreenState extends State<AccountCredentialsScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();

  bool _isLoading = true;
  UserModel? _user;

  _EditingField? _editingField;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _loadUser() async {
    final user = await AppRepository.instance.getCurrentUser(widget.userId);
    if (!mounted) return;

    if (user != null) {
      _emailController.text = user.email;
      _passwordController.text = user.password;
    }

    setState(() {
      _user = user;
      _isLoading = false;
    });
  }

  void _startEdit(_EditingField field) {
    if (_isLoading || _user == null) return;
    setState(() {
      _editingField = field;
    });
  }

  void _cancelEdit() {
    final current = _user;
    if (current != null) {
      _emailController.text = current.email;
      _passwordController.text = current.password;
    }

    setState(() {
      _editingField = null;
    });
  }

  String _maskPassword(String password) {
    if (password.isEmpty) return '';
    final clampedLength = password.length > 12 ? 12 : password.length;
    return List.filled(clampedLength, '*').join();
  }

  String _editingTitle() {
    switch (_editingField) {
      case _EditingField.email:
        return 'Chỉnh sửa tên đăng nhập';
      case _EditingField.password:
        return 'Đổi mật khẩu';
      default:
        return '';
    }
  }

  Future<void> _saveEmail() async {
    final current = _user;
    if (current == null) return;

    final email = _emailController.text.trim();
    if (email.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng nhập tên đăng nhập.')),
      );
      return;
    }

    if (AppConfig.isFull) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Chỉnh sửa tài khoản chưa hỗ trợ trên server (chỉ ở bản demo).')),
      );
      return;
    }
    final updated = current.copyWith(email: email);
    try {
      await SqfliteHelper.instance.updateUser(updated);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã cập nhật tài khoản.')),
      );
      setState(() {
        _user = updated;
        _editingField = null;
      });
    } catch (e) {
      if (!mounted) return;
      final msg = e.toString().toLowerCase();
      if (msg.contains('unique') || msg.contains('1555') || msg.contains('2067')) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Email đã tồn tại.')),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Cập nhật thất bại: $e')),
        );
      }
    }
  }

  Future<void> _changePassword({
    required String currentPassword,
    required String newPassword,
    required String confirmPassword,
  }) async {
    final current = _user;
    if (current == null) return;

    if (currentPassword.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng nhập mật khẩu cũ.')),
      );
      return;
    }
    if (newPassword.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng nhập mật khẩu mới.')),
      );
      return;
    }
    if (confirmPassword.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng xác nhận mật khẩu mới.')),
      );
      return;
    }
    if (currentPassword != current.password) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Mật khẩu cũ không đúng.')),
      );
      return;
    }
    if (newPassword.length < 6) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Mật khẩu mới phải có ít nhất 6 ký tự.')),
      );
      return;
    }
    if (newPassword != confirmPassword) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Mật khẩu mới không khớp.')),
      );
      return;
    }

    if (AppConfig.isFull) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đổi mật khẩu chưa hỗ trợ trên server (chỉ ở bản demo).')),
      );
      return;
    }
    final updated = current.copyWith(password: newPassword);
    try {
      await SqfliteHelper.instance.updateUser(updated);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đổi mật khẩu thành công.')),
      );
      setState(() {
        _user = updated;
        _editingField = null;
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Đổi mật khẩu thất bại: $e')),
      );
    }
  }

  Widget _buildEditableInfoRow({
    required String label,
    required String value,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
        child: Row(
          children: [
            SizedBox(
              width: 160,
              child: Text(
                label,
                style: const TextStyle(color: AppTheme.textGray),
              ),
            ),
            Expanded(
              child: Text(
                value,
                style: const TextStyle(
                  color: AppTheme.textWhite,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            const SizedBox(width: 10),
            const Icon(
              Icons.edit_rounded,
              size: 20,
              color: AppTheme.mintAccent,
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = _user;

    return Scaffold(
      backgroundColor: AppTheme.navyDark,
      appBar: AppBar(title: const Text('Tài khoản')),
      body: _isLoading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.mintAccent),
            )
          : (user == null)
          ? const Center(
              child: Text(
                'Chưa có dữ liệu tài khoản.',
                style: TextStyle(color: AppTheme.textGray),
              ),
            )
          : ListView(
              padding: const EdgeInsets.all(24.0),
              children: [
                const Icon(
                  Icons.lock_outline_rounded,
                  size: 72,
                  color: AppTheme.mintAccent,
                ),
                const SizedBox(height: 16),
                const Text(
                  'Chạm vào dòng cần sửa.',
                  style: TextStyle(fontSize: 16, color: AppTheme.textGray),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 20),
                Card(
                  margin: EdgeInsets.zero,
                  child: Column(
                    children: [
                      _buildEditableInfoRow(
                        label: 'Tên đăng nhập',
                        value: user.email,
                        onTap: () => _startEdit(_EditingField.email),
                      ),
                      Divider(
                        height: 1,
                        color: Colors.white.withValues(alpha: 0.06),
                      ),
                      _buildEditableInfoRow(
                        label: 'Mật khẩu',
                        value: _maskPassword(user.password),
                        onTap: () => _startEdit(_EditingField.password),
                      ),
                    ],
                  ),
                ),
                if (_editingField == _EditingField.password) ...[
                  const SizedBox(height: 24),
                  const Text(
                    'Đổi mật khẩu',
                    style: TextStyle(
                      color: AppTheme.textWhite,
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 16),
                  _PasswordChangeForm(
                    onSave: _changePassword,
                    onCancel: _cancelEdit,
                  ),
                ],
                if (_editingField == _EditingField.email) ...[
                  const SizedBox(height: 24),
                  const Text(
                    'Chỉnh sửa tên đăng nhập',
                    style: TextStyle(
                      color: AppTheme.textWhite,
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 16),
                  CustomTextField(
                    controller: _emailController,
                    label: 'Tên đăng nhập (Email)',
                    keyboardType: TextInputType.emailAddress,
                  ),
                  const SizedBox(height: 20),
                  PrimaryButton(text: 'Lưu thay đổi', onPressed: _saveEmail),
                  const SizedBox(height: 8),
                  TextButton(
                    onPressed: _cancelEdit,
                    child: const Text(
                      'Hủy',
                      style: TextStyle(color: AppTheme.textGray),
                    ),
                  ),
                ],
              ],
            ),
    );
  }
}

class _PasswordChangeForm extends StatefulWidget {
  final Future<void> Function({
    required String currentPassword,
    required String newPassword,
    required String confirmPassword,
  }) onSave;
  final VoidCallback onCancel;

  const _PasswordChangeForm({
    required this.onSave,
    required this.onCancel,
  });

  @override
  State<_PasswordChangeForm> createState() => _PasswordChangeFormState();
}

class _PasswordChangeFormState extends State<_PasswordChangeForm> {
  final _currentController = TextEditingController();
  final _newController = TextEditingController();
  final _confirmController = TextEditingController();
  bool _isLoading = false;
  bool _obscureCurrent = true;
  bool _obscureNew = true;
  bool _obscureConfirm = true;

  @override
  void dispose() {
    _currentController.dispose();
    _newController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  Future<void> _handleSave() async {
    setState(() => _isLoading = true);
    await widget.onSave(
      currentPassword: _currentController.text,
      newPassword: _newController.text,
      confirmPassword: _confirmController.text,
    );
    if (!mounted) return;
    setState(() => _isLoading = false);
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        CustomTextField(
          controller: _currentController,
          label: 'Mật khẩu cũ',
          isPassword: true,
          obscureText: _obscureCurrent,
          obscuringCharacter: '*',
          suffixIcon: IconButton(
            icon: Icon(
              _obscureCurrent ? Icons.visibility_off : Icons.visibility,
              color: AppTheme.textGray,
            ),
            onPressed: () => setState(() => _obscureCurrent = !_obscureCurrent),
          ),
        ),
        const SizedBox(height: 12),
        CustomTextField(
          controller: _newController,
          label: 'Mật khẩu mới',
          isPassword: true,
          obscureText: _obscureNew,
          obscuringCharacter: '*',
          suffixIcon: IconButton(
            icon: Icon(
              _obscureNew ? Icons.visibility_off : Icons.visibility,
              color: AppTheme.textGray,
            ),
            onPressed: () => setState(() => _obscureNew = !_obscureNew),
          ),
        ),
        const SizedBox(height: 12),
        CustomTextField(
          controller: _confirmController,
          label: 'Xác nhận mật khẩu mới',
          isPassword: true,
          obscureText: _obscureConfirm,
          obscuringCharacter: '*',
          suffixIcon: IconButton(
            icon: Icon(
              _obscureConfirm ? Icons.visibility_off : Icons.visibility,
              color: AppTheme.textGray,
            ),
            onPressed: () => setState(() => _obscureConfirm = !_obscureConfirm),
          ),
        ),
        const SizedBox(height: 20),
        PrimaryButton(
          text: 'Lưu thay đổi',
          onPressed: _isLoading ? () {} : _handleSave,
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: widget.onCancel,
          child: const Text(
            'Hủy',
            style: TextStyle(color: AppTheme.textGray),
          ),
        ),
      ],
    );
  }
}
