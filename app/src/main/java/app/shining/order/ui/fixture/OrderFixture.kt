package app.shining.order.ui.fixture

import app.shining.order.domain.model.MealType
import app.shining.order.ui.model.OrderCardUIModel
import app.shining.order.ui.model.OrderFoodDetailUIModel
import app.shining.order.ui.model.OrderScreenUIModel
import kotlinx.collections.immutable.toImmutableList


val orderFoodDetailUIModelSelectedFixture1 = OrderFoodDetailUIModel(
    foodName = "قیمه بادمجان",
    price = 12000,
    programId = 0,
    selfId = 0,
    mealTypeId = 0,
    foodTypeId = 0,
    selfName = "مرکزی - سلف دانشجویان آقا",
    isSelected = true,
)

val orderFoodDetailUIModelNotSelectedFixture1 = OrderFoodDetailUIModel(
    foodName = "کباب کوبیده",
    price = 12000,
    programId = 0,
    selfId = 0,
    mealTypeId = 0,
    foodTypeId = 0,
    selfName = "سلف آزاد - دنا",
    isSelected = false,
)

val orderFoodDetailUIModelSelectedFixture2 = OrderFoodDetailUIModel(
    foodName = "جوجه کباب",
    price = 8000,
    programId = 0,
    selfId = 0,
    mealTypeId = 0,
    foodTypeId = 0,
    selfName = "مرکزی - سلف دانشجویان آقا",
    isSelected = true,
)

val orderFoodDetailUIModelNotSelectedFixture2 = OrderFoodDetailUIModel(
    foodName = "باقالی پلو با گوشت",
    price = 12000,
    programId = 0,
    selfId = 0,
    mealTypeId = 0,
    foodTypeId = 0,
    selfName = "سلف آزاد - لاله",
    isSelected = false,
)

val orderCardUIModelFixture1 = OrderCardUIModel(
    farsiDayName = "جمعه",
    date = "8-8-8",
    dayName = "Friday",
    reserveInfoList = mapOf(
        MealType.LUNCH to listOf(
            orderFoodDetailUIModelSelectedFixture1,
            orderFoodDetailUIModelNotSelectedFixture1,
        ).toImmutableList(),
        MealType.DINNER to listOf(
            orderFoodDetailUIModelSelectedFixture2,
        ).toImmutableList(),
    )
)

val orderCardUIModelFixture2 = OrderCardUIModel(
    farsiDayName = "شنبه",
    date = "2020-10-10",
    dayName = "Saturday",
    reserveInfoList = mapOf(
        MealType.LUNCH to listOf(
            orderFoodDetailUIModelSelectedFixture1,
            orderFoodDetailUIModelNotSelectedFixture2,

            ).toImmutableList(),
        MealType.DINNER to listOf(
            orderFoodDetailUIModelSelectedFixture2,
            orderFoodDetailUIModelNotSelectedFixture1,
        ).toImmutableList(),
    )
)

val orderScreenUIModelFixture = OrderScreenUIModel(
    orderCardUIModelList = listOf(
        orderCardUIModelFixture1,
        orderCardUIModelFixture2,
    ).toImmutableList()
)
