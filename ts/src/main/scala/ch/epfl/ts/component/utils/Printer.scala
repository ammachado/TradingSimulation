package ch.epfl.ts.component.utils

import ch.epfl.ts.component.Component
import ch.epfl.ts.data.{ DelOrder, LimitAskOrder, LimitBidOrder, LimitOrder, OHLC, Transaction, Quote }
import ch.epfl.ts.evaluation.EvaluationReport
import ch.epfl.ts.data.EndOfFetching
/**
 * Simple printer component. Prints what it receives
 * @param name The name of the printer.
 */
class Printer(val name: String) extends Component {
  override def receiver = {
    case t: Transaction               => println("Printer " + name + ": Transaction\t" + System.currentTimeMillis + "\t" + t.toString)
    case lb: LimitBidOrder            => println("Printer " + name + ": Limit Bid Order\t" + System.currentTimeMillis() + "\t" + lb.toString)
    case la: LimitAskOrder            => println("Printer " + name + ": Limit Ask Order\t" + System.currentTimeMillis() + "\t" + la.toString)
    case del: DelOrder                => println("Printer " + name + ": Delete Order\t" + System.currentTimeMillis() + "\t" + del.toString)
    case ohlc: OHLC                   => println("Printer " + name + ": OHLC\t" + System.currentTimeMillis() + "\t" + ohlc.toString)
    case quote: Quote                 => println("Printer " + name + ": Quote\t" + System.currentTimeMillis() + "\t" + quote.toString)
    case evalReport: EvaluationReport => println("Printer " + name + ": EvalReport\t" + System.currentTimeMillis() + "\t" + evalReport.toString)
    case endSignal: EndOfFetching     => println("Printer " + name + ": EndOfFetching\t" + System.currentTimeMillis() + "\t" + endSignal)
    case _                            => println("Printer " + name + ": received unknown")
  }
}
