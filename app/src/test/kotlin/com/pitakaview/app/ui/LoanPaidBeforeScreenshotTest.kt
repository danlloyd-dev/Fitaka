package com.pitakaview.app.ui

import androidx.compose.foundation.text.input.TextFieldState
import com.github.takahirom.roborazzi.captureRoboImage
import com.pitakaview.app.domain.DarkModePref
import com.pitakaview.app.domain.LoanDirection
import com.pitakaview.app.domain.LoanPlan
import com.pitakaview.app.domain.LoanProblem
import com.pitakaview.app.domain.ThemeId
import com.pitakaview.app.ui.theme.FitakaTheme
import com.pitakaview.app.ui.utang.LoanEditorContent
import com.pitakaview.app.ui.utang.LoanEditorUi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The loan form with what was paid before the app, and a loan paid twice a month (made-up amounts). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1700dp-hdpi")
class LoanPaidBeforeScreenshotTest {
    private val today = LocalDate.of(2026, 11, 27)

    private fun editor(name: String, plan: LoanPlan, paid: String, problems: Set<LoanProblem> = emptySet(), theme: ThemeId = ThemeId.MANGO, mode: DarkModePref = DarkModePref.LIGHT) =
        captureRoboImage("build/outputs/roborazzi/screen_loan_paid_before_$name.png") {
            FitakaTheme(theme, mode) {
                LoanEditorContent(
                    ui = LoanEditorUi(
                        isEdit = false, direction = LoanDirection.I_OWE, startDate = LocalDate.of(2026, 6, 1), plan = plan,
                        dueDate = LocalDate.of(2026, 7, 15), wallets = emptyList(), walletId = null, moneyChosen = true, today = today,
                        problems = problems,
                    ),
                    who = TextFieldState("SSS loan"), amount = TextFieldState("12000"), total = TextFieldState(""),
                    installment = TextFieldState(if (plan == LoanPlan.TWICE_A_MONTH) "500" else "1000"), note = TextFieldState(""),
                    paidCount = TextFieldState(if (plan.inInstallments) paid else ""), paidAmount = TextFieldState(if (plan.inInstallments) "" else paid),
                    onDirection = {}, onStartDate = {}, onPlan = {}, onDueDate = {}, onMoney = {}, onSave = {}, onToggleSettled = {}, onDelete = {}, onBack = {},
                )
            }
        }

    @Test fun monthlyWithMonthsPaid() = editor("monthly_mango_light", LoanPlan.MONTHLY, "4")
    @Test fun twiceAMonthWithPaymentsPaid() = editor("twice_mango_light", LoanPlan.TWICE_A_MONTH, "5")
    @Test fun twiceAMonthInTheDark() = editor("twice_ube_dark", LoanPlan.TWICE_A_MONTH, "5", theme = ThemeId.UBE, mode = DarkModePref.DARK)
    @Test fun noDueDateWithAnAmountPaid() = editor("amount_mango_light", LoanPlan.NO_DATE, "4000")
    @Test fun tooMuchPaid() = editor("too_much_mango_light", LoanPlan.MONTHLY, "13", problems = setOf(LoanProblem.PAID_BEFORE))
}
