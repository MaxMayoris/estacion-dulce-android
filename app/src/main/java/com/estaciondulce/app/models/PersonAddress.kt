import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PersonAddress(
    val formattedAddress: String = "",  // La dirección completa y formateada, e.g., "1600 Amphitheatre Parkway, Mountain View, CA 94043, USA"
    val placeId: String = "",           // Identificador único de Google Places (opcional)
    val latitude: Double? = null,       // Latitud de la ubicación (opcional)
    val longitude: Double? = null,      // Longitud de la ubicación (opcional)
    val street: String? = null,         // Nombre y número de la calle
    val city: String? = null,           // Ciudad
    val state: String? = null,          // Estado o provincia
    val postalCode: String? = null,     // Código postal
    val country: String? = null         // País
) : Parcelable
