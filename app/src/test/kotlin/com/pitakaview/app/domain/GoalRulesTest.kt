package com.pitakaview.app.domain

import com.pitakaview.app.data.db.entity.SavingsGoalEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The form rules for savings goals added after the review before 1.0 (2026-11-27). */
class GoalRulesTest {
    private val now = Instant.parse("2026-11-25T08:00:00Z")
    private val nov3 = LocalDate.of(2026, 11, 3)

    @Test
    fun anOldDepositCannotBeLoweredBelowWhatHasNotBeenTakenOut() {
        fun problems(pesos: String) = MoveFormLogic.problems(MoveForm(pesos, walletId = 1, date = nov3), TxPurpose.SAVINGS_DEPOSIT, leastCentavos = 300_000)
        assertEquals(setOf(MoveProblem.LESS_THAN_TAKEN_OUT), problems("2999.99"))
        assertEquals(emptySet<MoveProblem>(), problems("3000"))
        assertEquals(emptySet<MoveProblem>(), problems("9000"))
        assertEquals("a withdrawal is not held to it", emptySet<MoveProblem>(),
            MoveFormLogic.problems(MoveForm("1", walletId = 1, date = nov3), TxPurpose.SAVINGS_WITHDRAWAL, leastCentavos = 300_000))
    }

    @Test
    fun aGoalsWalletStaysOnceSomethingIsLoggedForIt() {
        val kept = SavingsGoalEntity(id = 4, name = "Laptop", walletId = 7, createdAt = now)
        val form = GoalForm(name = "Laptop", walletId = 9)
        assertEquals("fixed: the wallet it was kept in", 7L, GoalFormLogic.toEntity(form, kept, now, walletFixed = true).walletId)
        assertEquals("fixed at No wallet too", null, GoalFormLogic.toEntity(form, kept.copy(walletId = null), now, walletFixed = true).walletId)
        assertEquals("nothing logged yet: it can change", 9L, GoalFormLogic.toEntity(form, kept, now, walletFixed = false).walletId)
        assertEquals("a new goal takes the form's", 9L, GoalFormLogic.toEntity(form, null, now, walletFixed = true).walletId)
        assertEquals("the rest still changes", "Laptop for school", GoalFormLogic.toEntity(form.copy(name = "Laptop for school"), kept, now, walletFixed = true).name)
    }
}
