import android.os.Parcelable
import com.estaciondulce.app.models.RecipeProduct
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Recipe section containing grouped ingredients with quantities.
 */
@Parcelize
data class RecipeSection(
    val id: String = "",
    val name: String = "",
    var products: @RawValue List<RecipeProduct> = listOf()
) : Parcelable
