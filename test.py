from app import hello

def test_app():
  message = hello()
  assert message == "Hello World!"
