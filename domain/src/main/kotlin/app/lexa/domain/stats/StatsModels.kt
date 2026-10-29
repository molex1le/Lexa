package app.lexa.domain.stats

import app.lexa.domain.model.AnswerTotals

/** Words that left box 5 today, this week (from Monday) and this month. */
data class LearnedPeriods(val today: Int = 0, val week: Int = 0, val month: Int = 0)

/** Answer totals for this week (from Monday) and for all time. */
data class Accuracy(val week: AnswerTotals = AnswerTotals(), val all: AnswerTotals = AnswerTotals())
