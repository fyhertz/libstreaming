package net.majorkernelpanic.streaming.audio;

public class AACNotSupportedException extends RuntimeException {

	private static final long serialVersionUID = -2637694735773976250L;

	public AACNotSupportedException() {
		super("AAC not supported by this phone !");
	}
	
}
