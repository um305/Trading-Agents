package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedResearchReport::class,
        WatchlistItem::class,
        AiModelEntity::class,
        AiCompanyEntity::class,
        DiscoveredModelEntity::class,
        CryptoPair::class,
        TradingSignal::class,
        TradeOutcomeEntity::class,
        CachedCandleEntity::class,
        MasterVerdictEntity::class,
        ArmedPlanEntity::class,
        PlanOutcomeHistoryEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun researchReportDao(): ResearchReportDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun aiCompanyDao(): AiCompanyDao
    abstract fun cryptoPairDao(): CryptoPairDao
    abstract fun tradingSignalDao(): TradingSignalDao
    abstract fun tradeOutcomeDao(): TradeOutcomeDao
    abstract fun cachedCandleDao(): CachedCandleDao
    abstract fun masterVerdictDao(): MasterVerdictDao
    abstract fun armedPlanDao(): ArmedPlanDao
    abstract fun planOutcomeHistoryDao(): PlanOutcomeHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trading_agents_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
