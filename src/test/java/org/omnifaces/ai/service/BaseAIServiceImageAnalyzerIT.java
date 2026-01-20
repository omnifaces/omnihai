package org.omnifaces.ai.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

abstract class BaseAIServiceImageAnalyzerIT extends AIServiceIT {

    private static final String OMNIFACES_LOGO = ""
            + "iVBORw0KGgoAAAANSUhEUgAAAFoAAABaCAMAAAAPdrEwAAAApVBMVEUAAAAAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIA"
            + "AgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIA"
            + "AgIAAgIAAQEAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIAAgIXVCu3AAAAN3RSTlMA4uTo3r7rBZHL1Q/R3NnD"
            + "Je0vHsemarp0tY2YVUU28LCqop2Ib2FLQRgMUT0JejIUfjopW4NdS1Ta4QAABLBJREFUWMPtWdl2qjAUNScBmSeZccSx12qr1f7/p10D"
            + "dVVMBKI83Ie7X9pV0u3JGffB3r+BPkWve/SdZeCrfrD86pp4oGAMCCHARHE6Zd5oGF0hq9sOqUcA6BfYGHbG7FioAlk6duWOAFAVrqJ3"
            + "Q/2B0R3AWHdDnSIGk3031D5LLc8HnbgascCJ1wX1G49aGndBrfOoo49OfK1xfD3rptoDltpddJPYK4YZyKkT5i+FpVZHXTCvZxgxMPPN"
            + "6+kRqxhxINvhnxfbR4agYJrgX2/g4i/WayPhlOCSOQjtSTllZNmPyt9gun6+DM9GQQxo7/THy6nmTlxttvzwFqQ0XI2fTMH3gJQm+9s/"
            + "xdw9jkajsbO7BGDlu0VNmvlTQzi0oWCeZB7bsY7Bj1Mk8Vk2SLWCGKPwjfv8YOJyTi4EM2U0LZ3hSpsd/8Ru68tFIMyp11h06+/TprSw"
            + "v1QRBUxS53Eb/8oBytJc9UqMt6vh8f4SuzBRDc1Q/fxSwc7chDJKp8/6ciqdAlqmX1x0lgoKKfUqySBZiJoAQDRlGaEyfsm436R87JKb"
            + "SO+hasIPhXr4deLpRmeAaZU/5fOgRerPf8SPYcEvB8mutx0RYMeINmwV+X5oAmIASvnPnwb70J15rduMDSy1uSyeLQAx2H8KNMfs0cTX"
            + "CdsxlbeeCBaEJadmb4Ezr8VUxpxjHc3HM8dVRGyGqByP0BqdA0fSCXXiPxqH2r5YN+NRC/UznUMN9qWsMw61K2R1n2c1Vfcxx9fau5Cv"
            + "bQ41lVfvwImB82ryyWn/ch2V4ZbjnRD12GLv/V3orntq7I9fVYU4HxRRmOLqJ6KwL6rd1Dujr6ulY0OFefHZE8XQqLojvD7wEvJruLZ4"
            + "64kjuyEGNbzp6AvblDGGa2jFEZWS7QJQg3U1yPtgKmlARfkz1APKTOwkSmbKUGcfj1P32d0qLJTxduA5+gMpLSME1jO7/YxSqzX39eiA"
            + "dg/iHvm0qD/mdSeoR+TAEc89Qqlrr/vtPrfaz9EFVm05HOlwl5fCq3ZRMNP6HJrLz6z2G5P6I2wQLBOEwKfdWripkgaDRlRfYao8hUeB"
            + "1HDImdH0S8X6k2cVqqjh1G4/EX9tEJuUurGIt1Rawla8NanN4jAqClJkP3GKgZ42a4qiRU1FdrYVdTWMWhzEosIsILQUWwzqd9qiJqGA"
            + "LCvm4rzN9pzJdGxKUqKc9MbIxHnkU6OhVSdeyj/Tl1hq3K+VH4FhEQTFab1VVG42GxLVVG94qxCCFjm1rW42/kPuuCKZSNLIfbxbeiB6"
            + "EPrv++UqbwqiIqMqIObrparkYoYMizW7BZo6N9iIgV1fiweXFdvfvJmRy4ixYdPUUxkYEoPINxEDcqjNVB/Q0yBBbZVrgJ5HVEttvEKd"
            + "1K/ymHdTBiZBDBj11Bx4FCkM0pxzPRLXioWYST5+k99lrA2WV9+tVXzP7O/4o4Uxe9rQ1/eMs4cP3gzdH9SOLYTILSB7JMYkqJwj++b1"
            + "MgG42cZy/WH3tW8PWmkbLZRp128tsVr3bcMoMDGUB8GO272DWOW2BhgZUrau17XxzLcAiJGkrQWovgkP5/1q3Dyj18vDOT55vf9ogb89"
            + "f09H/KHQdAAAAABJRU5ErkJggg==";

    @Test
    void analyzeImage() {
        var response = service.analyzeImage(Base64.getDecoder().decode(OMNIFACES_LOGO), "What shape is this?");
        log(response);
        assertTrue(response.toLowerCase().contains("pentagon")
                || response.toLowerCase().contains("star"), response);
    }

    @Test
    void generateAltText() {
        var response = service.generateAltText(Base64.getDecoder().decode(OMNIFACES_LOGO));
        log(response);
        assertTrue(response.toLowerCase().contains("pentagon")
                || response.toLowerCase().contains("star"), response);
        assertTrue(response.split("\\s+").length <= 30, response);
    }
}
