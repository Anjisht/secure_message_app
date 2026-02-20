package roy.ij.obscure

import java.lang.Error

data class SignInResult(
    val data: roy.ij.obscure.UserData?,
    val errorMessage: String?
)

data class UserData(
    val userId: String,
    val userName: String?,
    val ppurl : String?,
    val email: String?
)

data class AppState(
    val isSignedIn: Boolean = false,
    val userData: UserData?= null,
    val signInError: String?= null,
    val srEmail: String = ""
)