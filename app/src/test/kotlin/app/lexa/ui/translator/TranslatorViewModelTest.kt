package app.lexa.ui.translator

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lexa.domain.model.HistoryItem
import app.lexa.domain.model.Translation
import app.lexa.domain.repository.AddedWord
import app.lexa.domain.repository.DictionaryRepository
import app.lexa.domain.repository.HistoryRepository
import app.lexa.domain.repository.LookupError
import app.lexa.domain.repository.LookupProgress
import app.lexa.domain.repository.LookupResult
import app.lexa.domain.repository.TranslatedWordRepository
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private class FakeDictionary : DictionaryRepository {
    val asked = mutableListOf<String>()
    val unknown = mutableSetOf<String>()
    override suspend fun lookup(query: String, onProgress: (LookupProgress) -> Unit): LookupResult {
        asked += query
        return if (query.trim() in unknown) LookupResult.Failed(LookupError.NOTHING_FOUND)
        else LookupResult.Found(Translation(term = query.trim()), fromCache = false)
    }
}

private class FakeWords : TranslatedWordRepository {
    val live = mutableMapOf<String, Long>()
    private var next = 1L
    override suspend fun addFromTranslation(translation: Translation): AddedWord {
        live[translation.term]?.let { return AddedWord(it, isNew = false, isFavorite = false) }
        val id = next++
        live[translation.term] = id
        return AddedWord(id, isNew = true, isFavorite = false)
    }
    override suspend fun undoAdd(wordId: Long) { live.values.remove(wordId) }
    override suspend fun setFavorite(wordId: Long, favorite: Boolean) = Unit
}

private object NoHistory : HistoryRepository {
    override fun observeHistory(): Flow<List<HistoryItem>> = MutableStateFlow(emptyList())
    override suspend fun record(query: String, wordId: Long?) = Unit
    override suspend fun clear() = Unit
}

/** Typing translates by itself: after a pause, at once on paste, and without leaving half-typed words behind. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TranslatorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dictionary = FakeDictionary()
    private val words = FakeWords()

    @Before fun main() = Dispatchers.setMain(dispatcher)
    @After fun reset() = Dispatchers.resetMain()

    /** The screen collects [TranslatorViewModel.state]; without a collector it stays at its first value. */
    private fun TestScope.viewModel() = TranslatorViewModel(
        dictionary, words, NoHistory,
        Pronouncer(ApplicationProvider.getApplicationContext()),
        SavedStateHandle(),
    ).also { vm -> backgroundScope.launch { vm.state.collect {} } }

    private fun TestScope.type(vm: TranslatorViewModel, text: String) {
        var typed = ""
        text.forEach { typed += it; vm.onQueryChange(typed) }
        runCurrent()
    }

    private fun TestScope.pause() {
        advanceTimeBy(TranslatorViewModel.AUTO_DELAY_MS + 100)
        runCurrent()
    }

    @Test fun `translates after the pause, not while typing`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "commute")
        advanceTimeBy(TranslatorViewModel.AUTO_DELAY_MS - 100)
        runCurrent()
        assertTrue(dictionary.asked.isEmpty())
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("commute"), dictionary.asked)
        assertIs<TranslatorPhase.Result>(vm.state.value.phase)
    }

    @Test fun `a keystroke restarts the wait`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "car")
        advanceTimeBy(TranslatorViewModel.AUTO_DELAY_MS - 100)
        vm.onQueryChange("carp")
        runCurrent()
        advanceTimeBy(TranslatorViewModel.AUTO_DELAY_MS - 100)
        runCurrent()
        assertTrue(dictionary.asked.isEmpty())
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("carp"), dictionary.asked)
    }

    @Test fun `a paste translates at once`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onQueryChange("reluctant")
        runCurrent()
        assertEquals(listOf("reluctant"), dictionary.asked)
    }

    @Test fun `the search key translates at once`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "wander")
        vm.onTranslate()
        runCurrent()
        assertEquals(listOf("wander"), dictionary.asked)
    }

    @Test fun `typing on past an added word takes it back out`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "car")
        pause()
        assertTrue("car" in words.live)
        vm.onQueryChange("carp")
        runCurrent()
        assertTrue("car" !in words.live)
        assertTrue("carp" !in words.live)
    }

    @Test fun `a word that stays is kept, and clearing the field clears the result`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "car")
        pause()
        vm.onQueryChange("")
        runCurrent()
        assertTrue("car" in words.live)
        assertEquals(TranslatorPhase.Idle, vm.state.value.phase)
    }

    @Test fun `an unknown word is not added`() = runTest(dispatcher) {
        dictionary.unknown += "commu"
        val vm = viewModel()
        type(vm, "commu")
        pause()
        assertTrue(words.live.isEmpty())
        assertIs<TranslatorPhase.Error>(vm.state.value.phase)
    }

    @Test fun `the same word is not looked up twice in a row`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "wander")
        pause()
        vm.onQueryChange("wander ")
        pause()
        assertEquals(listOf("wander"), dictionary.asked)
    }
}
