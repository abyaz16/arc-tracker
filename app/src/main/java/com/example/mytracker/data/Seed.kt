package com.example.mytracker.data

object Seed {

    val categories = listOf(
        Category("cat-work", "Work", "c1"),
        Category("cat-study", "Study", "c4"),
        Category("cat-health", "Health", "c2"),
        Category("cat-personal", "Personal", "c5"),
        Category("cat-admin", "Admin", "c3"),
    )

    fun empty(): Db = Db(categories = categories)

    fun demo(): Db = empty()
}
