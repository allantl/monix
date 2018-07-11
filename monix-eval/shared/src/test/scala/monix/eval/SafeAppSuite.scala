/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
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

package monix.eval


import cats.effect.ExitCode
import minitest.SimpleTestSuite
import monix.eval.Task.Options
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.Promise

object SafeAppSuite extends SimpleTestSuite {
  test("run works") {
    var wasExecuted = false
    val app = new SafeApp {
      override def run(args: List[String]) =
        Task {
          wasExecuted = args.headOption.getOrElse("unknown") == "true"
          ExitCode.Success
        }
    }

    app.main(Array("true"))
    assert(wasExecuted, "wasExecuted")
  }

  testAsync("options are configurable") {
    val opts = Task.defaultOptions
    assert(!opts.localContextPropagation, "!opts.localContextPropagation")
    val opts2 = opts.enableLocalContextPropagation
    assert(opts2.localContextPropagation, "opts2.localContextPropagation")
    val p = Promise[Options]()

    val app = new SafeApp {
      override val options = opts2

      def run(args: List[String]): Task[ExitCode] =
        for (opts <- Task.readOptions) yield {
          p.success(opts)
          ExitCode.Success
        }
    }

    app.main(Array.empty)
    for (r <- p.future) yield {
      assertEquals(r, opts2)
    }
  }

  testAsync("ConcurrentEffect[Task]") {
    val wasExecuted = Promise[Boolean]()
    val app = new SafeApp {
      def run(args: List[String]) = {
        Task.fromIO(
          Task.async[ExitCode] { cb => wasExecuted.success(true); cb.onSuccess(ExitCode.Success) }
            .executeAsync
            .toIO)
      }
    }

    app.main(Array("true"))
    for (r <- wasExecuted.future) yield {
      assert(r, "wasExecuted == true")
    }
  }
}