package stocks

import akka.NotUsed
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Source

import scala.concurrent.duration._
import yahoofinance._

/**
 * A stock is a source of stock quotes and a symbol.
 */
class Stock(val symbol: String) {
  private val stockQuoteGenerator: StockQuoteGenerator = new FetchStockQuote(symbol)

  private val source: Source[StockQuote, NotUsed] = {
    Source.unfold(stockQuoteGenerator.seed) { (last: StockQuote) =>
      val next = stockQuoteGenerator.newQuote(last.symbol)
      Some(next, next)
    }
  }

  /**
   * Returns a source of stock history, containing a single element.
   */
  def history(n: Int): Source[StockHistory, NotUsed] = {
    source.grouped(n).map(sq => new StockHistory(symbol, sq.map(_.price))).take(1)
  }

  /**
   * Provides a source that returns a stock quote every 75 milliseconds.
   */
  def update: Source[StockUpdate, NotUsed] = {
    source
      .throttle(elements = 1, per = 1.second, maximumBurst = 1, ThrottleMode.shaping)
      .map(sq => new StockUpdate(sq.symbol, sq.price))
  }

  override val toString: String = s"Stock($symbol)"
}

trait StockQuoteGenerator {
  def seed: StockQuote
  def newQuote(symbol: String): StockQuote
}

class FetchStockQuote(symbol: String) extends StockQuoteGenerator {

  def seed: StockQuote = {
    StockQuote(symbol, 0)
  }

  def newQuote(symbol: String): StockQuote = {
    var newFetch = YahooFinance.get(symbol)
    StockQuote(symbol, newFetch.getQuote().getPrice())
  }
}

case class StockQuote(symbol: String, price: BigDecimal)

// JSON presentation class for stock history
case class StockHistory(symbol: String, prices: Seq[BigDecimal])

object StockHistory {
  import play.api.libs.json._ // Combinator syntax

  implicit val stockHistoryWrites: Writes[StockHistory] = new Writes[StockHistory] {
    override def writes(history: StockHistory): JsValue = Json.obj(
      "type" -> "stockhistory",
      "symbol" -> history.symbol,
      "history" -> history.prices
    )
  }
}

// JSON presentation class for stock update
case class StockUpdate(symbol: String, price: BigDecimal)

object StockUpdate {
  import play.api.libs.json._ // Combinator syntax

  implicit val stockUpdateWrites: Writes[StockUpdate] = new Writes[StockUpdate] {
    override def writes(update: StockUpdate): JsValue = Json.obj(
      "type" -> "stockupdate",
      "symbol" -> update.symbol,
      "price" -> update.price
    )
  }
}
