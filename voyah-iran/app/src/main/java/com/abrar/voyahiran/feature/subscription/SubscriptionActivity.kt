package com.abrar.voyahiran.feature.subscription

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.abrar.voyahiran.R
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.databinding.ActivitySubscriptionBinding
import com.abrar.voyahiran.ui.MainActivity
import kotlinx.coroutines.launch

class SubscriptionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding=ActivitySubscriptionBinding.inflate(layoutInflater);setContentView(binding.root)
        val manager=(application as VoyahIranApplication).subscriptions
        SubscriptionService.start(this)
        binding.retry.setOnClickListener {
            binding.retry.visibility=View.GONE
            binding.statusIcon.visibility=View.GONE
            binding.contact.visibility=View.GONE
            binding.progress.visibility=View.VISIBLE
            binding.message.setText(R.string.subscription_checking)
            manager.refresh()
        }
        binding.exit.setOnClickListener { finishAffinity() }
        lifecycleScope.launch {
            manager.state.collect { state ->
                if(state==SubscriptionStatus.ACTIVE && manager.allowed) {
                    // Automatic renewal returns to the background. An explicit app launch continues into its menus.
                    if(intent.getBooleanExtra("userLaunch",false)) startActivity(Intent(this@SubscriptionActivity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    finish();return@collect
                }
                binding.progress.visibility=if(state==SubscriptionStatus.CHECKING)View.VISIBLE else View.GONE
                val checking=state==SubscriptionStatus.CHECKING
                val blocked=state==SubscriptionStatus.BLOCKED
                binding.retry.visibility=if(!checking && !blocked) View.VISIBLE else View.GONE
                binding.exit.visibility=if(blocked) View.VISIBLE else View.GONE
                binding.statusIcon.visibility=if(checking) View.GONE else View.VISIBLE
                binding.statusIcon.setImageResource(if(blocked) R.drawable.ic_subscription_blocked else R.drawable.ic_subscription_error)
                binding.contact.visibility=if(checking) View.GONE else View.VISIBLE
                binding.message.setText(when(state) {
                    SubscriptionStatus.BLOCKED->R.string.subscription_blocked
                    SubscriptionStatus.VEHICLE_UNAVAILABLE->R.string.subscription_vin_error
                    SubscriptionStatus.CHECKING->R.string.subscription_checking
                    else->R.string.subscription_offline
                })
            }
        }
    }
}
