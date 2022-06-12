package tech.dzolotov.counterappmvi

import javax.inject.Inject

interface IDescriptionRepository {
    suspend fun getDescription(): String
}

class DescriptionRepository @Inject constructor() : IDescriptionRepository {
    override suspend fun getDescription() = "Text from external data source"
}
