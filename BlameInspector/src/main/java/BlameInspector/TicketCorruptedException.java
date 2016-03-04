/**
 * Created by Alexander on 03.03.2016.
 */
public class TicketCorruptedException extends Throwable {

    private String message;

    public TicketCorruptedException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage(){
        return message;
    }
}
