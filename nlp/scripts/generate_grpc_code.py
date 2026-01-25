import subprocess
import os
import sys

def generate_python_code():
    proto_dir = os.path.join(os.path.dirname(__file__), '..', 'proto')
    output_dir = os.path.join(os.path.dirname(__file__), '..', 'nlp_service')
    proto_file = os.path.join(proto_dir, 'reminder_parser.proto')

    command = [
        sys.executable, '-m', 'grpc_tools.protoc',
        f'-I{proto_dir}',
        f'--python_out={output_dir}',
        f'--grpc_python_out={output_dir}',
        proto_file
    ]

    print(f"Generating Python gRPC code from {proto_file}...")
    try:
        subprocess.run(command, check=True)
        print("Python code generated")

        fix_imports(output_dir)

    except subprocess.CalledProcessError as e:
        print(f"Error: {e}")

def generate_java_code():
    proto_dir = os.path.join(os.path.dirname(__file__), '..', 'proto')
    output_dir = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'java')
    proto_file = os.path.join(proto_dir, 'reminder_parser.proto')

    command = [
        'protoc',
        f'-I={proto_dir}',
        f'--java_out={output_dir}',
        proto_file
    ]

    print(f"\nGenerating Java gRPC code from {proto_file}...")
    try:
        subprocess.run(command, check=True)
        print("Java code generated")
    except subprocess.CalledProcessError as e:
        print(f"Error: {e}")
        print("Make sure protoc is installed:")
        print("  Windows: choco install protoc")
        print("  Linux: sudo apt-get install protobuf-compiler")

def fix_imports(output_dir):
    generated_files = [
        os.path.join(output_dir, 'reminder_parser_pb2.py'),
        os.path.join(output_dir, 'reminder_parser_pb2_grpc.py')
    ]

    for file_path in generated_files:
        if os.path.exists(file_path):
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()

            content = content.replace(
                'import reminder_parser_pb2 as reminder__parser__pb2',
                'from . import reminder_parser_pb2 as reminder__parser__pb2'
            )

            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)

            print(f"Fixed imports in {os.path.basename(file_path)}")

if __name__ == '__main__':
    print("=== Generating gRPC code ===")

    os.makedirs('proto', exist_ok=True)
    os.makedirs('nlp_service', exist_ok=True)

    generate_python_code()

    response = input("\nGenerate Java code for main application? (y/n): ")
    if response.lower() == 'y':
        generate_java_code()

    print("\n=== Done ===")