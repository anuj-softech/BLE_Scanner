package com.rise.blescanner.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rise.blescanner.databinding.ItemDiscoveredDeviceBinding
import com.rise.blescanner.models.BleDeviceUiInfo

class BleDeviceAdapter(
    private val onDeviceSelected: (BleDeviceUiInfo) -> Unit
) : RecyclerView.Adapter<BleDeviceAdapter.DeviceViewHolder>() {

    private val deviceList = mutableListOf<BleDeviceUiInfo>()

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        deviceList.clear()
        notifyDataSetChanged()
    }

    fun updateDevice(device: BleDeviceUiInfo) {
        val index = deviceList.indexOfFirst { it.macAddress == device.macAddress }
        if (index != -1) {
            deviceList[index] = device
            notifyItemChanged(index)
        } else {
            var insertIndex = deviceList.indexOfFirst { it.rssi < device.rssi }
            if (insertIndex == -1) {
                insertIndex = deviceList.size
            }
            deviceList.add(insertIndex, device)
            notifyItemInserted(insertIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDiscoveredDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(deviceList[position])
    }

    override fun getItemCount(): Int = deviceList.size

    inner class DeviceViewHolder(
        private val binding: ItemDiscoveredDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(device: BleDeviceUiInfo) {
            binding.txtDeviceName.text = device.name
            binding.txtDeviceMac.text = device.macAddress
            binding.txtDeviceRssi.text = "${device.rssi} dBm"
            binding.txtDeviceTxPower.text = device.txPower?.let { "Tx: $it" } ?: "Tx: --"

            binding.root.setOnClickListener {
                onDeviceSelected(device)
            }
        }
    }
}