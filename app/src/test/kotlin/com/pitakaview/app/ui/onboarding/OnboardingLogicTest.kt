package com.pitakaview.app.ui.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pitakaview.app.data.DefaultData
import com.pitakaview.app.data.db.FitakaDatabase
import com.pitakaview.app.data.db.FitakaDatabase_Impl
import com.pitakaview.app.data.db.entity.TransactionEntity
import com.pitakaview.app.data.db.entity.WalletEntity
import com.pitakaview.app.data.prefs.SettingsRepository
import com.pitakaview.app.domain.CategoryKind
import com.pitakaview.app.domain.TxPurpose
import com.pitakaview.app.domain.WalletProvider
import com.pitakaview.app.domain.WalletType
import com.pitakaview.app.ui.wallets.WalletPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** The rules of the first-run welcome, against real SQL and a real settings file. */
class OnboardingLogicTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: FitakaDatabase
    private lateinit var settings: SettingsRepository
    private val storeJob = Job()
    private val now = Instant.parse("2026-11-26T04:00:00Z")

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder { FitakaDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        // What a fresh install has: starter categories and an empty Cash wallet.
        DefaultData.seedIfEmpty(db, now)
        settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + storeJob), produceFile = { File(tmp.root, "settings.preferences_pb") }),
        )
    }

    @After
    fun close() = runBlocking {
        db.close()
        storeJob.cancelAndJoin()
    }

    private suspend fun wallets() = db.walletDao().all()

    @Test
    fun anEmptyBoxMeansZeroAndOnlyRealAmountsPass() {
        assertEquals(0L, OnboardingLogic.amount(""))
        assertEquals(0L, OnboardingLogic.amount("   "))
        assertEquals(150_000L, OnboardingLogic.amount("1500"))
        assertEquals(25_050L, OnboardingLogic.amount("250.50"))
        assertNull(OnboardingLogic.amount("abc"))
        assertNull(OnboardingLogic.amount("-5"))

        assertEquals(emptySet<WalletPreset>(), OnboardingLogic.problems(OnboardingForm("1500", mapOf(WalletPreset.GCASH to ""))))
        assertEquals(
            "each box that does not read as an amount is named",
            setOf(WalletPreset.CASH, WalletPreset.MAYA),
            OnboardingLogic.problems(OnboardingForm("12.345", mapOf(WalletPreset.GCASH to "20", WalletPreset.MAYA to "x"))),
        )
    }

    @Test
    fun thePlanKeepsTheOfferedOrderAndOnlyThePickedWallets() {
        val plan = OnboardingLogic.plan(OnboardingForm("1500", linkedMapOf(WalletPreset.MARIBANK to "3000", WalletPreset.GCASH to "250.50")))
        assertEquals(150_000L, plan.cashCentavos)
        assertEquals(listOf(WalletPreset.GCASH to 25_050L, WalletPreset.MARIBANK to 300_000L), plan.wallets)
        assertEquals(OnboardingPlan(0, emptyList()), OnboardingLogic.plan(OnboardingForm()))
    }

    @Test
    fun finishingSetsTheCashOnHandAndAddsThePickedWallets() = runBlocking {
        OnboardingLogic.apply(db, OnboardingPlan(150_000, listOf(WalletPreset.GCASH to 25_050, WalletPreset.MARIBANK to 0)), now)

        val all = wallets()
        assertEquals("the starter Cash wallet is used, not a second one", listOf("Cash", "GCash", "MariBank"), all.map { it.name })
        assertEquals(listOf(150_000L, 25_050L, 0L), all.map { it.openingBalanceCentavos })
        assertEquals(listOf(WalletType.CASH, WalletType.E_WALLET, WalletType.BANK), all.map { it.type })
        assertEquals("the provider is what file imports match on later", listOf(WalletProvider.NONE, WalletProvider.GCASH, WalletProvider.MARIBANK), all.map { it.provider })
        assertEquals("in the order they are shown", all.map { it.sortOrder }.sorted(), all.map { it.sortOrder })
        // The balances the rest of the app sees.
        assertEquals(listOf(150_000L, 25_050L, 0L), db.walletDao().observeActiveWithBalance().first().map { it.balanceCentavos })
    }

    @Test
    fun aWalletThatIsAlreadyThereIsGivenTheAmountInsteadOfBeingAddedTwice() = runBlocking {
        db.walletDao().insert(WalletEntity(name = "My GCash", type = WalletType.E_WALLET, provider = WalletProvider.GCASH, openingBalanceCentavos = 10_000, sortOrder = 1, createdAt = now))

        OnboardingLogic.apply(db, OnboardingPlan(0, listOf(WalletPreset.GCASH to 25_050, WalletPreset.MAYA to 500)), now)
        // Done twice (a double tap) changes nothing more.
        OnboardingLogic.apply(db, OnboardingPlan(0, listOf(WalletPreset.GCASH to 25_050, WalletPreset.MAYA to 500)), now)

        val all = wallets()
        assertEquals(listOf("Cash", "My GCash", "Maya"), all.map { it.name })
        assertEquals(listOf(0L, 25_050L, 500L), all.map { it.openingBalanceCentavos })
    }

    @Test
    fun aHiddenCashWalletIsNotBroughtBackANewOneIsMade() = runBlocking {
        val cash = wallets().single()
        db.walletDao().update(cash.copy(isArchived = true))

        OnboardingLogic.apply(db, OnboardingPlan(70_000, emptyList()), now)

        val all = wallets()
        assertEquals(2, all.size)
        assertTrue("the hidden one is left as it was", all.first { it.id == cash.id }.isArchived)
        assertEquals(70_000L, all.first { it.id != cash.id }.openingBalanceCentavos)
    }

    @Test
    fun theWelcomeIsShownOnlyUntilItIsDoneAndNeverToSomeoneWithEntries() = runBlocking {
        assertTrue("a fresh install", OnboardingLogic.isNeeded(settings, db))
        assertFalse("asking does not mark it done", settings.onboardingDone.first())

        settings.setOnboardingDone()
        assertFalse(OnboardingLogic.isNeeded(settings, db))
    }

    @Test
    fun someoneWhoAlreadyHasEntriesNeverSeesItAndIsNotAskedAgain() = runBlocking {
        val cash = wallets().single().id
        val food = db.categoryDao().observeActive(CategoryKind.EXPENSE).first().first().id
        db.transactionDao().insert(
            TransactionEntity(purpose = TxPurpose.EXPENSE, amountCentavos = 5_000, occurredOn = LocalDate.of(2026, 11, 26), fromWalletId = cash, categoryId = food, createdAt = now),
        )

        assertFalse(OnboardingLogic.isNeeded(settings, db))
        assertTrue("remembered, so later launches do not ask the database", settings.onboardingDone.first())
    }
}
