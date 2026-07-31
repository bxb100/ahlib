package cn.ahlib.reservation.data

internal fun List<Category>.findReservationCategory(): Category? =
    firstNotNullOfOrNull(Category::findReservationCategory)

private fun Category.findReservationCategory(): Category? {
    if (
        categoryName == RESERVATION_CATEGORY_NAME ||
        idModel?.trim() == RESERVATION_MODEL_ID
    ) {
        return this
    }
    return childList.firstNotNullOfOrNull(Category::findReservationCategory)
}

private const val RESERVATION_MODEL_ID = "22"
private const val RESERVATION_CATEGORY_NAME = "\u5ea7\u4f4d\u9884\u7ea6"
