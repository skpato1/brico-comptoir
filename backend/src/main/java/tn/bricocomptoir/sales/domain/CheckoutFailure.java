package tn.bricocomptoir.sales.domain;

public final class CheckoutFailure extends RuntimeException {
    private final String code;
    public CheckoutFailure(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
