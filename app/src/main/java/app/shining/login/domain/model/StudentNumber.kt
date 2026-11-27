package app.shining.login.domain.model

import app.shining.domain.util.toEnglishNumber

/** Sharif student numbers have 8 or 9 digits. */
val STUDENT_NUMBER_LENGTHS = 8..9

/** How many characters [input] has once Persian digits and surrounding spaces are normalized. */
fun studentNumberLength(input: String): Int = input.toEnglishNumber().trim().length

fun isValidStudentNumber(input: String): Boolean =
    studentNumberLength(input) in STUDENT_NUMBER_LENGTHS
