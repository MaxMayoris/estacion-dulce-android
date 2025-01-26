import android.os.Parcelable
import com.estaciondulce.app.models.RecipeProduct
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class RecipeSection(
    val id: String = "",
    val name: String = "",
    var products: @RawValue List<RecipeProduct> = listOf()
) : Parcelable
