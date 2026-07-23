class BudgetModel {
  final int? id;
  final int categoryId;
  final String categoryName;
  final double limitAmount;
  final int month;
  final int year;
  final int? userId;

  const BudgetModel({
    this.id,
    required this.categoryId,
    required this.categoryName,
    required this.limitAmount,
    required this.month,
    required this.year,
    this.userId,
  });

  Map<String, dynamic> toMap() {
    return {
      if (id != null) 'id': id,
      'category_id': categoryId,
      'category_name': categoryName,
      'limit_amount': limitAmount,
      'month': month,
      'year': year,
      'user_id': userId,
    };
  }

  factory BudgetModel.fromMap(Map<String, dynamic> map) {
    return BudgetModel(
      id: map['id'] as int?,
      categoryId: map['category_id'] as int? ?? 0,
      categoryName: map['category_name'] as String? ?? '',
      limitAmount: (map['limit_amount'] as num?)?.toDouble() ?? 0.0,
      month: map['month'] as int? ?? 1,
      year: map['year'] as int? ?? 2026,
      userId: map['user_id'] as int?,
    );
  }

  BudgetModel copyWith({
    int? id,
    int? categoryId,
    String? categoryName,
    double? limitAmount,
    int? month,
    int? year,
    int? userId,
  }) {
    return BudgetModel(
      id: id ?? this.id,
      categoryId: categoryId ?? this.categoryId,
      categoryName: categoryName ?? this.categoryName,
      limitAmount: limitAmount ?? this.limitAmount,
      month: month ?? this.month,
      year: year ?? this.year,
      userId: userId ?? this.userId,
    );
  }
}
