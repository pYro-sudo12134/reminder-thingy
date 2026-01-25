import sys
import os
from pathlib import Path

current_dir = Path(__file__).parent
sys.path.insert(0, str(current_dir))

try:
    from nlp_service import reminder_parser_pb2
    from nlp_service import reminder_parser_pb2_grpc
    print("Protobuf modules loaded")
except ImportError as e:
    print(f"Error loading protobuf modules: {e}")
    print("Generating protobuf code...")
    try:
        from grpc_tools import protoc
        import subprocess

        proto_file = current_dir / "proto" / "reminder_parser.proto"
        if proto_file.exists():
            cmd = [
                sys.executable, "-m", "grpc_tools.protoc",
                f"-I{current_dir / 'proto'}",
                f"--python_out={current_dir}",
                f"--grpc_python_out={current_dir}",
                str(proto_file)
            ]
            subprocess.run(cmd, check=True)

            import shutil
            for file in ["reminder_parser_pb2.py", "reminder_parser_pb2_grpc.py"]:
                src = current_dir / file
                if src.exists():
                    dst = current_dir / "nlp_service" / file
                    shutil.move(src, dst)

            from nlp_service import reminder_parser_pb2
            from nlp_service import reminder_parser_pb2_grpc
            print("Protobuf code generated and loaded")
    except Exception as gen_error:
        print(f"Failed to generate protobuf code: {gen_error}")

def main():
    model_dir = current_dir / "models" / "intent_classifier"

    if model_dir.exists() and (model_dir / "best_model.pt").exists():
        print("ML models found")
        print("Starting server with ML enhancement...")

        try:
            from nlp_service.server_ml import serve
            serve()
        except ImportError as e:
            print(f"Import error for ML server: {e}")
            import traceback
            traceback.print_exc()
            print("\nTrying rule-based server...")
            from nlp_service.server import serve
            serve()
    else:
        print("ML models not found or incomplete")
        print("Starting rule-based server...")

        try:
            from nlp_service.server import serve
            serve()
        except ImportError as e:
            print(f"Import error: {e}")
            import traceback
            traceback.print_exc()
            print("\nTrying to run directly...")
            try:
                import nlp_service.server as server_module
                server_module.serve()
            except Exception as e2:
                print(f"Direct run failed: {e2}")
                sys.exit(1)

if __name__ == "__main__":
    main()