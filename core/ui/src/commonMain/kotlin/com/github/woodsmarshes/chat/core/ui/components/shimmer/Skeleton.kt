package com.github.woodsmarshes.chat.core.ui.components.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .shimmer(cornerRadius = 4.dp),
    )
}

@Composable
fun SkeletonCircle(
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .shimmer(cornerRadius = size / 2),
    )
}

@Composable
fun SkeletonRect(
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    height: Dp = 80.dp,
) {
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(8.dp))
            .shimmer(cornerRadius = 8.dp),
    )
}

@Composable
fun ConversationSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonCircle(size = 52.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonLine(height = 16.dp)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonLine(
                modifier = Modifier.fillMaxWidth(0.7f),
                height = 13.dp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        SkeletonLine(
            modifier = Modifier.width(40.dp),
            height = 12.dp,
        )
    }
}

@Composable
fun ContactSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonCircle(size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonLine(height = 15.dp)
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonLine(
                modifier = Modifier.fillMaxWidth(0.4f),
                height = 12.dp,
            )
        }
    }
}

@Composable
fun ListSkeleton(
    count: Int = 8,
    skeleton: @Composable () -> Unit = { ConversationSkeleton() },
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        repeat(count) {
            skeleton()
        }
    }
}
