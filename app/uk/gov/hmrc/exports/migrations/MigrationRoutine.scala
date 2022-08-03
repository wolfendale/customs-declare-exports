/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.exports.migrations

import com.google.inject.Singleton
import com.mongodb.client.{MongoClient, MongoClients}
import play.api.Logging
import uk.gov.hmrc.exports.config.AppConfig
import uk.gov.hmrc.exports.migrations.changelogs.cache.{
  MakeTransportPaymentMethodNotOptional,
  RemoveMeansOfTransportCrossingTheBorderNationality,
  RenameToAdditionalDocuments
}
import uk.gov.hmrc.exports.migrations.changelogs.emaildetails.RenameSendEmailDetailsToItem
import uk.gov.hmrc.exports.migrations.changelogs.notification.{MakeParsedDetailsOptional, SplitTheNotificationsCollection}
import uk.gov.hmrc.exports.migrations.changelogs.submission.{AddNotificationSummariesToSubmissions, RemoveRedundantIndexes}
import uk.gov.hmrc.exports.mongo.ExportsClient

import javax.inject.Inject

@Singleton
class MigrationRoutine @Inject() (val appConfig: AppConfig) extends ExportsClient with Logging {

  logger.info("Starting migration with ExportsMigrationTool")

  private val lockMaxTries = 10
  private val lockMaxWaitMillis = minutesToMillis(5)
  private val lockAcquiredForMillis = minutesToMillis(3)

  private val lockManagerConfig = LockManagerConfig(lockMaxTries, lockMaxWaitMillis, lockAcquiredForMillis)

  private val migrationsRegistry = MigrationsRegistry()
    .register(new MakeParsedDetailsOptional())
    .register(new SplitTheNotificationsCollection())
    .register(new RenameToAdditionalDocuments())
    .register(new MakeTransportPaymentMethodNotOptional())
    .register(new RemoveRedundantIndexes())
    .register(new RenameSendEmailDetailsToItem())
    .register(new AddNotificationSummariesToSubmissions())
    .register(new RemoveMeansOfTransportCrossingTheBorderNationality())

  ExportsMigrationTool(db, migrationsRegistry, lockManagerConfig).execute()

  client.close()

  private def minutesToMillis(minutes: Int): Long = minutes * 60L * 1000L
}
