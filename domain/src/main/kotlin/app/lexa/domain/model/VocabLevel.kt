package app.lexa.domain.model

/** The learner's rough vocabulary level; it picks which built-in words training offers. */
enum class VocabLevel { A1, A2, B1, B2, C1;

    /** This level, then the harder ones: when a level runs out, training moves up instead of stopping. */
    fun andAbove(): List<VocabLevel> = entries.drop(ordinal)
}
