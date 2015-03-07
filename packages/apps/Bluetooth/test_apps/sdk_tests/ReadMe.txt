The folder contains the integration test apps.
=======================================================================
By integration test we mean the kind of testing which directly test the APIs

exposed by the module.


This kind of test will be affected by all the interaction with other modules 

(stack, firmware e.t.c) , and when a test case fails, you'll need to dig in 

to find out which module does not behave as expected.