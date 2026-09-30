package com.algoprep.app.data.seed

import android.content.Context
import com.algoprep.app.core.AppJson
import com.algoprep.app.domain.hints.HintProvider
import com.algoprep.app.domain.hints.PatternHintTemplates
import com.algoprep.app.domain.hints.TemplateHintProvider
import com.algoprep.app.domain.model.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetHintProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : HintProvider {
    private val delegate: HintProvider by lazy {
        val json = context.assets.open("hints/pattern_hints.json").bufferedReader().use { it.readText() }
        val templates: PatternHintTemplates = AppJson.decodeFromString(json)
        TemplateHintProvider(templates) { Locale.getDefault().language }
    }

    override fun hintsFor(task: Task): List<String> = delegate.hintsFor(task)
}
