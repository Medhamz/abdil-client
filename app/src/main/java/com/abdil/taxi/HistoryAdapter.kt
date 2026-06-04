package com.abdil.taxi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.abdil.taxi.model.RideResponse

class HistoryAdapter(
    private var rides: List<RideResponse>,
    private val onSelectionChanged: (RideResponse, Boolean) -> Unit,
    private val onRateClick: (RideResponse) -> Unit  // ✅ NOUVEAU : Callback pour la notation
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val selectedPositions = mutableSetOf<Int>()

    fun updateList(newRides: List<RideResponse>) {
        rides = newRides
        selectedPositions.clear()
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedPositions.clear()
        notifyDataSetChanged()
    }

    fun selectAll() {
        if (selectedPositions.size == rides.size) {
            selectedPositions.clear()
        } else {
            selectedPositions.clear()
            for (i in rides.indices) {
                selectedPositions.add(i)
            }
        }
        notifyDataSetChanged()
    }

    fun getSelectedRideIds(): List<Long> {
        return selectedPositions.mapNotNull { position ->
            if (position < rides.size) rides[position].id else null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(rides[position], selectedPositions.contains(position))
    }

    override fun getItemCount(): Int = rides.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cbSelect: CheckBox = itemView.findViewById(R.id.cbSelect)
        private val tvRideId: TextView = itemView.findViewById(R.id.tvRideId)
        private val tvPickup: TextView = itemView.findViewById(R.id.tvPickup)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val btnRate: Button = itemView.findViewById(R.id.btnRate)  // ✅ NOUVEAU

        fun bind(ride: RideResponse, isSelected: Boolean) {
            tvRideId.text = "Course #${ride.id}"
            tvPickup.text = "Départ: ${ride.pickupAddress}"
            tvDestination.text = "Destination: ${ride.destinationAddress}"
            tvPrice.text = "Prix: ${ride.estimatedPrice} FCFA"

            // Affichage du statut avec style
            when (ride.status) {
                "PENDING" -> tvStatus.text = "Statut: ⏳ En attente"
                "ACCEPTED" -> tvStatus.text = "Statut: ✅ Acceptée"
                "STARTED" -> tvStatus.text = "Statut: 🚖 En cours"
                "COMPLETED" -> tvStatus.text = "Statut: 🏁 Terminée"
                "CANCELLED" -> tvStatus.text = "Statut: ❌ Annulée"
                else -> tvStatus.text = "Statut: ${ride.status}"
            }

            val dateStr = try {
                val parts = ride.createdAt.split("T")
                parts[0] + " à " + parts[1].split(".")[0].substring(0, 5)
            } catch (e: Exception) {
                ride.createdAt
            }
            tvDate.text = "Date: $dateStr"

            // ✅ NOUVEAU : Afficher le bouton "Noter" uniquement pour les courses terminées
            if (ride.status == "COMPLETED") {
                btnRate.visibility = View.VISIBLE
                btnRate.setOnClickListener {
                    onRateClick(ride)
                }
            } else {
                btnRate.visibility = View.GONE
            }

            cbSelect.isChecked = isSelected
            cbSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedPositions.add(adapterPosition)
                } else {
                    selectedPositions.remove(adapterPosition)
                }
                onSelectionChanged(ride, isChecked)
            }
        }
    }
}