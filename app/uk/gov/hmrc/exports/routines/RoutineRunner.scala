/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.exports.routines

import akka.actor.{ActorSystem, Cancellable}
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.exports.config.AppConfig

import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class RoutineRunner @Inject() (
  reattemptParsing: ReattemptNotificationParsingRoutine,
  actorSystem: ActorSystem,
  applicationLifecycle: ApplicationLifecycle,
  appConfig: AppConfig
)(implicit ec: RoutinesExecutionContext) {

  val scheduler = actorSystem.scheduler

  val initialTask: Cancellable = scheduler.scheduleOnce(0.seconds) {
    for {
      _ <- reattemptParsing.execute()
    } yield (())
  }
  applicationLifecycle.addStopHook(() => Future.successful(initialTask.cancel()))

  val randomInitalDelay: FiniteDuration = Random.nextInt(30).seconds

  val periodicTask: Cancellable = scheduler.scheduleWithFixedDelay(randomInitalDelay, appConfig.notificationReattemptInterval) { () =>
    reattemptParsing.execute()
  }
  applicationLifecycle.addStopHook(() => Future.successful(periodicTask.cancel()))
}
