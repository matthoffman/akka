package akka.performance.microbench

import akka.performance.workbench.PerformanceSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics
import org.junit.runner.RunWith
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.dispatch.Dispatchers
import akka.dispatch.Dispatcher
import akka.dispatch.Dispatchers

// -server -Xms512M -Xmx1024M -XX:+UseParallelGC -Dbenchmark=true -Dbenchmark.repeatFactor=500
@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class TellThroughputPerformanceSpec extends PerformanceSpec {
  import TellThroughputPerformanceSpec._

  val clientDispatcher = app.dispatcherFactory.newDispatcher("client-dispatcher")
    .withNewThreadPoolWithLinkedBlockingQueueWithUnboundedCapacity
    .setCorePoolSize(maxClients)
    .build

  val destinationDispatcher = app.dispatcherFactory.newDispatcher("destination-dispatcher")
    .withNewThreadPoolWithLinkedBlockingQueueWithUnboundedCapacity
    .setCorePoolSize(maxClients)
    .build

  val repeat = 30000L * repeatFactor

  "Tell" must {
    "warmup" in {
      runScenario(4, warmup = true)
    }
    "warmup more" in {
      runScenario(4, warmup = true)
    }
    "perform with load 1" in {
      runScenario(1)
    }
    "perform with load 2" in {
      runScenario(2)
    }
    "perform with load 4" in {
      runScenario(4)
    }
    "perform with load 6" in {
      runScenario(6)
    }
    "perform with load 8" in {
      runScenario(8)
    }
    "perform with load 10" in {
      runScenario(10)
    }
    "perform with load 12" in {
      runScenario(12)
    }
    "perform with load 14" in {
      runScenario(14)
    }
    "perform with load 16" in {
      runScenario(16)
    }

    def runScenario(numberOfClients: Int, warmup: Boolean = false) {
      if (acceptClients(numberOfClients)) {

        val latch = new CountDownLatch(numberOfClients)
        val repeatsPerClient = repeat / numberOfClients
        val destinations = for (i ← 0 until numberOfClients)
          yield app.actorOf(Props(new Destination).withDispatcher(destinationDispatcher))
        val clients = for (dest ← destinations)
          yield app.actorOf(Props(new Client(dest, latch, repeatsPerClient)).withDispatcher(clientDispatcher))

        val start = System.nanoTime
        clients.foreach(_ ! Run)
        val ok = latch.await((5000000 + 500 * repeat) * timeDilation, TimeUnit.MICROSECONDS)
        val durationNs = (System.nanoTime - start)

        if (!warmup) {
          ok must be(true)
          logMeasurement(numberOfClients, durationNs, repeat)
        }
        clients.foreach(_ ! PoisonPill)
        destinations.foreach(_ ! PoisonPill)

      }
    }
  }
}

object TellThroughputPerformanceSpec {

  case object Run
  case object Msg

  class Destination extends Actor {
    def receive = {
      case Msg ⇒ sender ! Msg
    }
  }

  class Client(
    actor: ActorRef,
    latch: CountDownLatch,
    repeat: Long) extends Actor {

    var sent = 0L
    var received = 0L

    def receive = {
      case Msg ⇒
        received += 1
        if (sent < repeat) {
          actor ! Msg
          sent += 1
        } else if (received >= repeat) {
          latch.countDown()
        }
      case Run ⇒
        for (i ← 0L until math.min(1000L, repeat)) {
          actor ! Msg
          sent += 1
        }
    }

  }

}